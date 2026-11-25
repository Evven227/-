import asyncio, asyncpg, os

async def main():
    # 口令从环境变量读取，不要把明文口令提交到 Git。
    pw = os.environ.get('ADC_DB_PASSWORD', '请替换为你的数据库口令')
    c = await asyncpg.connect(
        f'postgresql://postgres:{pw}@127.0.0.1:5433/adc_dental')

    await c.execute("""
        UPDATE "MATERIAL" SET unit_cost = CASE material_code
            WHEN 'MAT-GLOVE' THEN 45.00
            WHEN 'MAT-MASK'  THEN 30.00
            WHEN 'MAT-FLOSS' THEN 12.00 END
    """)

    await c.execute("""
        UPDATE "APPOINTMENT" SET status='BOOKED', cancelled_at=NULL
         WHERE clinic_id=1 AND slot_code='AM1'
           AND starts_at::date = DATE '2026-10-27'
           AND status='CANCELLED'
    """)

    rem = await c.fetchval(
        "SELECT remaining FROM \"V_SLOT_AVAILABILITY\" "
        "WHERE clinic_id=1 AND slot_date=DATE '2026-10-27' AND slot_code='AM1'")
    print('AM1 余量还原为:', rem)

    for r in await c.fetch(
            'SELECT clinic_name, biz_date, booked_cnt, revenue, cost, profit '
            'FROM "V_CLINIC_DASHBOARD" WHERE revenue>0 ORDER BY biz_date'):
        print('  ', dict(r))

    await c.close()

asyncio.run(main())
